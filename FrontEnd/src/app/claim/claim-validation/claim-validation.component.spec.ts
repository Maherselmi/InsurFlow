import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClaimValidationComponent } from './claim-validation.component';

describe('ClaimValidationComponent', () => {
  let component: ClaimValidationComponent;
  let fixture: ComponentFixture<ClaimValidationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClaimValidationComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ClaimValidationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
